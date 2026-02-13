package com.paucasesnoves.steamAPI.modules.web.exception;


import com.paucasesnoves.steamAPI.exception.ResourceNotFoundException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(basePackages = "com.paucasesnoves.steamAPI.modules.web.controller")
public class GlobalWebExceptionHandler {

    // 404 - Recurso no encontrado (lanzado por servicio)
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }

    // 400 - Error de tipo de argumento (path variable con formato incorrecto, ej: letra en lugar de número)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, Model model) {
        String message = String.format("El paràmetre '%s' amb valor '%s' no és vàlid.", ex.getName(), ex.getValue());
        model.addAttribute("error", message);
        return "error/400";
    }

    // 404 - Ruta no encontrada (NoResourceFoundException)
    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResourceFound(NoResourceFoundException ex, Model model) {
        model.addAttribute("error", "La ruta sol·licitada no existeix.");
        return "error/404";
    }

    // 500 - Qualsevol altra excepció no controlada
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        // Pots loguejar l'excepció
        model.addAttribute("error", "S'ha produït un error intern al servidor.");
        return "error/500";
    }
}